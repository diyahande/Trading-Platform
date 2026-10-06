import { ApiProperty } from '@nestjs/swagger';
import { IsString, Length, MaxLength } from 'class-validator';

export class ChangePasswordRequestDto {
  @ApiProperty({ maxLength: 128 })
  @IsString()
  @MaxLength(128)
  oldPassword!: string;

  @ApiProperty({ minLength: 12, maxLength: 128 })
  @IsString()
  @Length(12, 128)
  newPassword!: string;

  @ApiProperty({ minLength: 12, maxLength: 128 })
  @IsString()
  @Length(12, 128)
  confirmNewPassword!: string;
}
