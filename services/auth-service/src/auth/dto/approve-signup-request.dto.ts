import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsString, IsUUID, Length } from 'class-validator';

export class ApproveSignupRequestDto {
  @ApiProperty({ format: 'uuid' })
  @IsUUID('4')
  accountId!: string;

  @ApiPropertyOptional({ maxLength: 1000 })
  @IsString()
  @Length(0, 1000)
  reviewNotes = '';
}
