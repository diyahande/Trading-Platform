import { ApiProperty } from '@nestjs/swagger';
import { IsString, Length } from 'class-validator';

export class RefreshRequestDto {
  @ApiProperty()
  @IsString()
  @Length(1, 4096)
  refreshToken!: string;
}
